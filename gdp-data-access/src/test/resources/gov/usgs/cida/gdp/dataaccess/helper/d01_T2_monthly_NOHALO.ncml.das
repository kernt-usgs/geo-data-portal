Attributes {
    T2MAX {
        Int32 FieldType 104;
        String MemoryOrder "XY ";
        String description "Maximum temperature at 2 meter height";
        String units "K";
        String stagger "";
        String coordinates "XLONG XLAT";
        String cell_methods "time: maximum";
    }
    T2MEAN {
        Int32 FieldType 104;
        String MemoryOrder "XY ";
        String description "Mean temperature at 2 meter height";
        String units "K";
        String stagger "";
        String coordinates "XLONG XLAT";
        String cell_methods "time: mean";
    }
    T2MIN {
        Int32 FieldType 104;
        String MemoryOrder "XY ";
        String description "Minimum temperature at 2 meter height";
        String units "K";
        String stagger "";
        String coordinates "XLONG XLAT";
        String cell_methods "time: minimum";
    }
    Time {
        String long_name "Time";
        String units "days since 1870-01-01 00:00:00";
        String calendar "noleap";
        String bounds "time_bnds";
    }
    Times {
        String long_name "Datetime string";
        DODS {
            Int32 strlen 19;
            String dimName "DateStrLen";
        }
    }
    XLAT {
        Int32 FieldType 104;
        String MemoryOrder "XY ";
        String description "LATITUDE, SOUTH IS NEGATIVE";
        String units "degree_north";
        String stagger "";
    }
    XLONG {
        Int32 FieldType 104;
        String MemoryOrder "XY ";
        String description "LONGITUDE, WEST IS NEGATIVE";
        String units "degree_east";
        String stagger "";
    }
    time_bnds {
        String long_name "Time interval endpoints";
    }
    NC_GLOBAL {
        String TITLE " OUTPUT FROM WRF V3.4.1 MODEL";
        String START_DATE "2000-08-08_00:00:00";
        Int32 WEST-EAST_GRID_DIMENSION 180;
        Int32 SOUTH-NORTH_GRID_DIMENSION 136;
        Int32 BOTTOM-TOP_GRID_DIMENSION 36;
        Float32 DX 36000.0;
        Float32 DY 36000.0;
        String GRIDTYPE "C";
        Int32 DIFF_OPT 1;
        Int32 KM_OPT 4;
        Int32 DAMP_OPT 3;
        Float32 DAMPCOEF 0.2;
        Float32 KHDIF 0.0;
        Float32 KVDIF 0.0;
        Int32 MP_PHYSICS 6;
        Int32 RA_LW_PHYSICS 3;
        Int32 RA_SW_PHYSICS 3;
        Int32 SF_SFCLAY_PHYSICS 11;
        Int32 SF_SURFACE_PHYSICS 2;
        Int32 BL_PBL_PHYSICS 1;
        Int32 CU_PHYSICS 1;
        Int32 SURFACE_INPUT_SOURCE 1;
        Int32 SST_UPDATE 1;
        Int32 GRID_FDDA 0;
        Int32 GFDDA_INTERVAL_M 0;
        Int32 GFDDA_END_H 0;
        Int32 GRID_SFDDA 0;
        Int32 SGFDDA_INTERVAL_M 0;
        Int32 SGFDDA_END_H 0;
        Int32 HYPSOMETRIC_OPT 2;
        Int32 WEST-EAST_PATCH_START_UNSTAG 1;
        Int32 WEST-EAST_PATCH_END_UNSTAG 179;
        Int32 WEST-EAST_PATCH_START_STAG 1;
        Int32 WEST-EAST_PATCH_END_STAG 180;
        Int32 SOUTH-NORTH_PATCH_START_UNSTAG 1;
        Int32 SOUTH-NORTH_PATCH_END_UNSTAG 135;
        Int32 SOUTH-NORTH_PATCH_START_STAG 1;
        Int32 SOUTH-NORTH_PATCH_END_STAG 136;
        Int32 BOTTOM-TOP_PATCH_START_UNSTAG 1;
        Int32 BOTTOM-TOP_PATCH_END_UNSTAG 35;
        Int32 BOTTOM-TOP_PATCH_START_STAG 1;
        Int32 BOTTOM-TOP_PATCH_END_STAG 36;
        Int32 GRID_ID 1;
        Int32 PARENT_ID 1;
        Int32 I_PARENT_START 1;
        Int32 J_PARENT_START 1;
        Int32 PARENT_GRID_RATIO 1;
        Float32 DT 216.0;
        Float32 CEN_LAT 35.000004;
        Float32 CEN_LON -97.5;
        Float32 TRUELAT1 20.0;
        Float32 TRUELAT2 50.0;
        Float32 MOAD_CEN_LAT 35.000004;
        Float32 STAND_LON -97.5;
        Float32 POLE_LAT 90.0;
        Float32 POLE_LON 0.0;
        Float32 GMT 0.0;
        Int32 JULYR 2000;
        Int32 JULDAY 221;
        Int32 MAP_PROJ 1;
        String MMINLU "USGS";
        Int32 NUM_LAND_CAT 28;
        Int32 ISWATER 16;
        Int32 ISLAKE 28;
        Int32 ISICE 24;
        Int32 ISURBAN 1;
        Int32 ISOILWATER 14;
        String history "Fri Nov 30 15:24:32 2012: ncks -d west_east,10,168 -d south_north,10,124 ../with_HALO/wrfc36_d01_T2_monthly_2000-09_2050-12.nc wrfc36km_d01_T2_monthly_2000-09_2050-12_NOHALO.nc
Fri Nov 30 12:42:58 2012: /usr/local/bin/ncap2 -O -s T2MIN@cell_methods=\"time: minimum\"; T2MIN@description=\"Minimum temperature at 2 meter height\" -s T2MEAN@cell_methods=\"time: mean\"; T2MEAN@description=\"Mean temperature at 2 meter height\" -s T2MAX@cell_methods=\"time: maximum\"; T2MAX@description=\"Maximum temperature at 2 meter height\" wrfc36_d01_T2_monthly_2000-09_2050-12.nc wrfc36_d01_T2_monthly_2000-09_2050-12.nc
./postproc_single.py -b 2000-09-01 -e 2050-12-31 -m 2000-08-09 -d /scratch2/tmp -i wrfc36_d01_T2_daily_2000-08-08_2050-12-31.nc -o wrfc36_d01_T2_monthly_2000-09_2050-12.nc -v T2MIN,T2MEAN,T2MAX,Times,XLAT,XLONG";
        String institution "USGS
Rapid City Water Science Center
1608 Mountain View
Rapid City, SD  57702
jstamm@usgs.gov
pnorton@usgs.gov";
        Int32 nco_openmp_thread_number 1;
        String SIMULATION_START_DATE "2000-08-08_00:00:00";
        Int32 SF_URBAN_PHYSICS 0;
        Int32 SHCU_PHYSICS 0;
        Int32 MFSHCONV 0;
        Int32 FEEDBACK 0;
        Int32 SMOOTH_OPTION 0;
        Float32 SWRAD_SCAT 1.0;
        Int32 W_DAMPING 0;
        Int32 MOIST_ADV_OPT 1;
        Int32 SCALAR_ADV_OPT 1;
        Int32 TKE_ADV_OPT 1;
        Int32 DIFF_6TH_OPT 2;
        Float32 DIFF_6TH_FACTOR 0.12;
        Int32 OBS_NUDGE_OPT 0;
        Float32 BUCKET_MM 100.0;
        Float32 BUCKET_J 1.0E9;
        Float32 PREC_ACC_DT 180.0;
        Int32 OMLCALL 0;
        Int32 ISFTCFLX 0;
        Int32 ISHALLOW 0;
        Int32 DFI_OPT 0;
        String NCO "4.0.8";
    }
    DODS_EXTRA {
        String Unlimited_Dimension "Time";
    }
}
